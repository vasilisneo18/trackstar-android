package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.AuthResponse
import com.vasilisneo.trackstar.data.api.CheckEmailRequest
import com.vasilisneo.trackstar.data.api.ForgotPasswordRequest
import com.vasilisneo.trackstar.data.api.LoginRequest
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.RegisterRequest
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}

// Thin repository over AuthApi: runs the call, maps HTTP/network errors to a user-facing
// message, and persists the auth response to TokenStore on success.
class AuthRepository(private val tokenStore: TokenStore) {

    private val api = NetworkClient.authApi

    suspend fun checkEmail(email: String): ApiResult<Boolean> =
        when (val r = safeCall { api.checkEmail(CheckEmailRequest(email.trim().lowercase())) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.exists)
            is ApiResult.Error -> r
        }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        val result = safeCall { api.login(LoginRequest(email.trim().lowercase(), password)) }
        if (result is ApiResult.Success) tokenStore.save(result.data)
        return result
    }

    suspend fun register(request: RegisterRequest): ApiResult<AuthResponse> {
        val result = safeCall { api.register(request) }
        if (result is ApiResult.Success) tokenStore.save(result.data)
        return result
    }

    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> = safeCall {
        api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
    }

    // --- helpers ---

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(parseError(response))
            }
        } catch (e: IOException) {
            ApiResult.Error("No internet connection. Please try again.")
        } catch (e: Exception) {
            ApiResult.Error("Something went wrong. Please try again.")
        }
    }

    private fun parseError(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val parsed = raw?.let { runCatching { NetworkClient.gson.fromJson(it, MessageResponse::class.java) }.getOrNull() }
        return parsed?.message?.takeIf { it.isNotBlank() } ?: when (response.code()) {
            401 -> "Incorrect email or password."
            else -> "Something went wrong. Please try again."
        }
    }
}
