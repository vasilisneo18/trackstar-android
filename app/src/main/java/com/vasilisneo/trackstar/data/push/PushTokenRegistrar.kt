package com.vasilisneo.trackstar.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.RegisterPushTokenRequest
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import kotlinx.coroutines.tasks.await

// Sends this device's FCM registration token to the backend so it can push to us. Only registers
// when signed in (there's a session token) — the backend keys the token to the authenticated user.
// Call after login and on app launch if already logged in; the messaging service also calls it when
// FCM issues a new token.
object PushTokenRegistrar {

    suspend fun register(fcmToken: String? = null) {
        if (AuthTokenHolder.token == null) return
        val token = fcmToken ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        if (token.isNullOrBlank()) return
        runCatching {
            NetworkClient.notificationApi.registerToken(RegisterPushTokenRequest(token = token, platform = "android"))
        }
    }
}
