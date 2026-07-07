package com.vasilisneo.trackstar.data.auth

import android.content.Context
import com.vasilisneo.trackstar.data.api.AuthResponse

// Persists the signed-in user's tokens + basic identity, the Android equivalent of iOS's
// KeychainManager. Backed by plain SharedPreferences for now — TODO: move to
// EncryptedSharedPreferences (androidx.security) before release so the JWT isn't stored in
// cleartext.
class TokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("trackstar_auth", Context.MODE_PRIVATE)

    init {
        // Seed the interceptor's in-memory token from persisted prefs (survives relaunch).
        AuthTokenHolder.token = prefs.getString(KEY_TOKEN, null)
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
        }.apply()
        AuthTokenHolder.token = auth.token
    }

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val isLoggedIn: Boolean get() = token != null
    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val firstName: String? get() = prefs.getString(KEY_FIRST_NAME, null)
    val lastName: String? get() = prefs.getString(KEY_LAST_NAME, null)
    val role: String? get() = prefs.getString(KEY_ROLE, null)

    fun clear() {
        prefs.edit().clear().apply()
        AuthTokenHolder.token = null
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_USER_ID = "userId"
        const val KEY_EMAIL = "email"
        const val KEY_FIRST_NAME = "firstName"
        const val KEY_LAST_NAME = "lastName"
        const val KEY_ROLE = "role"
    }
}
