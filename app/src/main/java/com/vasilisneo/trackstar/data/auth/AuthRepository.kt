package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.AuthResponse
import com.vasilisneo.trackstar.data.api.CheckEmailRequest
import com.vasilisneo.trackstar.data.api.ForgotPasswordRequest
import com.vasilisneo.trackstar.data.api.LoginRequest
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.RegisterRequest

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
        val result = apiCall { api.login(LoginRequest(email.trim().lowercase(), password)) }
        if (result is ApiResult.Success) tokenStore.save(result.data)
        return result
    }

    suspend fun register(request: RegisterRequest): ApiResult<AuthResponse> {
        val result = apiCall { api.register(request) }
        if (result is ApiResult.Success) tokenStore.save(result.data)
        return result
    }

    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        apiCall { api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase())) }
}
