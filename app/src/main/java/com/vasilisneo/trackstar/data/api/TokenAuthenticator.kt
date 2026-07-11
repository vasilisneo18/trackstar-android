package com.vasilisneo.trackstar.data.api

import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// OkHttp Authenticator that silently refreshes an expired access token on a 401, then retries the
// original request — the Android equivalent of iOS's AuthInterceptor.retry(). Runs on OkHttp's
// background thread; the refresh call is intentionally blocking (Retrofit .execute()).
//
// Recursion is avoided by using a *separate* bare Retrofit/OkHttp for the refresh call (no auth
// interceptor, no authenticator), and by bailing out after one retry. Concurrent 401s are
// coalesced under a lock so only one refresh happens; the losers just retry with the new token.
class TokenAuthenticator : Authenticator {

    private interface RefreshApi {
        @POST("auth/refresh")
        fun refresh(@Body body: RefreshRequest): retrofit2.Call<AuthResponse>
    }

    // Bare client for /auth/refresh only — no interceptors/authenticator, so a failed refresh
    // can't re-enter this authenticator.
    private val refreshApi: RefreshApi by lazy {
        Retrofit.Builder()
            .baseUrl(NetworkClient.baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create(NetworkClient.gson))
            .build()
            .create(RefreshApi::class.java)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = AuthTokenHolder.refreshToken ?: return null

        // Give up after one refresh+retry attempt to avoid an auth loop.
        if (priorResponseCount(response) >= 2) return null

        synchronized(this) {
            val current = AuthTokenHolder.token
            val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            // Another thread already refreshed while we were waiting on the lock — just retry
            // with the token that's now current.
            if (current != null && current != usedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }

            val newAuth = runCatching { refreshApi.refresh(RefreshRequest(refreshToken)).execute() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()

            if (newAuth == null) {
                // Refresh token is also dead — clear session and let the app route to login.
                AuthTokenHolder.token = null
                AuthTokenHolder.refreshToken = null
                AuthTokenHolder.onSessionExpired?.invoke()
                return null
            }

            AuthTokenHolder.token = newAuth.token
            AuthTokenHolder.refreshToken = newAuth.refreshToken ?: refreshToken
            AuthTokenHolder.onTokensRefreshed?.invoke(newAuth.token, AuthTokenHolder.refreshToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newAuth.token}")
                .build()
        }
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
