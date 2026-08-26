package com.vasilisneo.trackstar.data.auth

import android.content.Context
import com.vasilisneo.trackstar.data.api.AuthResponse

// Persists the signed-in user's tokens + basic identity, the Android equivalent of iOS's
// KeychainManager. No raw password is ever stored — "Continue as" re-auths via the kept refresh
// token. The prefs file is excluded from backup (allowBackup=false) so tokens can't be exfiltrated
// via adb/cloud backup.
class TokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("trackstar_auth", Context.MODE_PRIVATE)

    init {
        // Migration: scrub the plaintext password older builds cached under "lastPassword" — we
        // never store the raw password anymore (Continue-as re-auths via the refresh token).
        if (prefs.contains(LEGACY_KEY_LAST_PASSWORD)) {
            prefs.edit().remove(LEGACY_KEY_LAST_PASSWORD).apply()
        }
        // Seed the networking layer's in-memory tokens from persisted prefs (survives relaunch).
        AuthTokenHolder.token = prefs.getString(KEY_TOKEN, null)
        AuthTokenHolder.refreshToken = prefs.getString(KEY_REFRESH, null)
        AuthTokenHolder.userId = prefs.getString(KEY_USER_ID, null)
        // Let the OkHttp Authenticator persist silently-refreshed tokens without a Context.
        AuthTokenHolder.onTokensRefreshed = { accessToken, refreshToken ->
            prefs.edit()
                .putString(KEY_TOKEN, accessToken)
                .apply { if (refreshToken != null) putString(KEY_REFRESH, refreshToken) }
                .apply()
        }
        // Refresh token also expired/invalid (e.g. signed in on another device): fully log out —
        // wipe the session + cached data and signal the UI to route to Landing immediately, so the
        // app never keeps running against a dead session (mirrors iOS's forced logout on expiry).
        AuthTokenHolder.onSessionExpired = {
            clearAll()
            com.vasilisneo.trackstar.data.billing.BillingManager.logOut()
            AuthTokenHolder.notifySessionExpired()
        }
    }

    fun save(auth: AuthResponse) {
        prefs.edit().apply {
            putString(KEY_TOKEN, auth.token)
            putString(KEY_REFRESH, auth.refreshToken)
            putString(KEY_USER_ID, auth.userId)
            putString(KEY_EMAIL, auth.email)
            putString(KEY_FIRST_NAME, auth.firstName)
            putString(KEY_LAST_NAME, auth.lastName)
            putString(KEY_ROLE, auth.role)
            putString(KEY_LAST_EMAIL, auth.email) // survives logout, for the "Continue as" card
        }.apply()
        AuthTokenHolder.token = auth.token
        AuthTokenHolder.refreshToken = auth.refreshToken
        AuthTokenHolder.userId = auth.userId
    }

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val isLoggedIn: Boolean get() = token != null
    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val firstName: String? get() = prefs.getString(KEY_FIRST_NAME, null)
    val lastName: String? get() = prefs.getString(KEY_LAST_NAME, null)
    val role: String? get() = prefs.getString(KEY_ROLE, null)

    val lastEmail: String? get() = prefs.getString(KEY_LAST_EMAIL, null)
    // "Continue as" is offered when we have a remembered email + a refresh token to re-auth with.
    // No raw password is ever stored — quickLogin exchanges the refresh token for a fresh session.
    val hasRememberedSession: Boolean get() = lastEmail != null && refreshToken != null

    /** Sign out: drop the access token + identity, but KEEP the refresh token + last email so the
     *  Landing screen can offer one-tap "Continue as" (re-auth via the refresh token, no password).
     *  Full wipe incl. the refresh token happens in clearAll(). */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN).remove(KEY_USER_ID)
            .remove(KEY_EMAIL).remove(KEY_FIRST_NAME).remove(KEY_LAST_NAME).remove(KEY_ROLE)
            .apply()
        AuthTokenHolder.token = null
        AuthTokenHolder.userId = null
        // refreshToken intentionally kept (in prefs + holder) so "Continue as" can re-auth.
        // Wipe the local cache so the next account doesn't see the previous user's data (mirrors
        // iOS's per-user isolated Realm).
        com.vasilisneo.trackstar.data.local.LocalStore.wipeAsync()
        // Detach the RevenueCat customer so the next sign-in starts clean (mirrors iOS logout).
        com.vasilisneo.trackstar.data.billing.BillingManager.logOut()
    }

    /** Full wipe including cached credentials — for Close Account / "Not you?". */
    fun clearAll() {
        prefs.edit().clear().apply()
        AuthTokenHolder.token = null
        AuthTokenHolder.refreshToken = null
        AuthTokenHolder.userId = null
        com.vasilisneo.trackstar.data.local.LocalStore.wipeAsync()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_USER_ID = "userId"
        const val KEY_EMAIL = "email"
        const val KEY_FIRST_NAME = "firstName"
        const val KEY_LAST_NAME = "lastName"
        const val KEY_ROLE = "role"
        const val KEY_LAST_EMAIL = "lastEmail"
        const val LEGACY_KEY_LAST_PASSWORD = "lastPassword" // scrubbed on init; never written anymore
    }
}
