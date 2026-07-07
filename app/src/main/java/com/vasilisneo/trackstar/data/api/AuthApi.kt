package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Public auth endpoints (/api/auth/**). Suspend so callers drive them from viewModelScope.
interface AuthApi {
    @POST("auth/check-email")
    suspend fun checkEmail(@Body body: CheckEmailRequest): Response<CheckEmailResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<MessageResponse>
}
