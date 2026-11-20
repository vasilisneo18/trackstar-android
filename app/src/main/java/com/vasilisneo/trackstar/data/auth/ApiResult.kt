package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    // `offline` is true only for connectivity failures (no network) — repositories fall back to the
    // local cache in that case, but NOT on real server/business errors (4xx/5xx), so stale data is
    // never shown in place of a legitimate error.
    // `code` is the HTTP status when the server answered.
    data class Error(val message: String, val offline: Boolean = false, val code: Int? = null) : ApiResult<Nothing> {
        /** The login was rejected — the request can succeed after signing in again. */
        val signedOut: Boolean get() = code != null && AuthTokenHolder.isSignedOutStatus(code)
    }
}

/** In-memory session tokens, read by the OkHttp auth interceptor + Authenticator. Kept in sync
 *  by TokenStore (seeded on app launch, updated on save/clear) so the networking layer never
 *  needs a Context. */
object AuthTokenHolder {
    @Volatile
    var token: String? = null

    @Volatile
    var refreshToken: String? = null

    /** Current signed-in backend userId, mirrored in memory (kept in sync by TokenStore) so the
     *  context-free repositories can scope their local cache per user without a Context. */
    @Volatile
    var userId: String? = null

    /** Set by TokenStore so the Authenticator can persist a silently-refreshed access/refresh
     *  token pair to disk (it runs off a background thread with no Context of its own). */
    @Volatile
    var onTokensRefreshed: ((accessToken: String, refreshToken: String?) -> Unit)? = null

    /** Set by the app so a failed refresh (refresh token also expired/invalid) can route back to
     *  login. Invoked from a background thread — implementations must hop to the main thread. */
    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    /** Emitted whenever the session becomes invalid (a token refresh failed — e.g. the account was
     *  signed in on another device). The UI collects this to force-logout to Landing, so the app
     *  never keeps running against a dead session (matches iOS's .sessionExpired handling). */
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired
    fun notifySessionExpired() { _sessionExpired.tryEmit(Unit) }

    /** An HTTP status meaning "not signed in": 401, or 403 once the dead login's token was dropped
     *  (requests then go without one). Such a write is kept and replayed after the next sign-in. */
    fun isSignedOutStatus(code: Int): Boolean = code == 401 || (code == 403 && token == null)
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
            ApiResult.Error(parseError(response), code = response.code())
        }
    } catch (e: IOException) {
        ApiResult.Error("No internet connection. Please try again.", offline = true)
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
