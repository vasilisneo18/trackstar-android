package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}

/** In-memory session tokens, read by the OkHttp auth interceptor + Authenticator. Kept in sync
 *  by TokenStore (seeded on app launch, updated on save/clear) so the networking layer never
 *  needs a Context. */
object AuthTokenHolder {
    @Volatile
    var token: String? = null

    @Volatile
    var refreshToken: String? = null

    /** Set by TokenStore so the Authenticator can persist a silently-refreshed access/refresh
     *  token pair to disk (it runs off a background thread with no Context of its own). */
    @Volatile
    var onTokensRefreshed: ((accessToken: String, refreshToken: String?) -> Unit)? = null

    /** Set by the app so a failed refresh (refresh token also expired/invalid) can route back to
     *  login. Invoked from a background thread — implementations must hop to the main thread. */
    @Volatile
    var onSessionExpired: (() -> Unit)? = null
}

/** Runs a Retrofit call and maps HTTP/network failures to a user-facing message. Shared by
 *  every repository. */
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> {
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
