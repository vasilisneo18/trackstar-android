package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Registers this device's FCM token with the backend (POST /api/notifications/token). `platform`
// tells the backend to route push via FCM (Android) rather than APNs.
interface NotificationApi {
    @POST("notifications/token")
    suspend fun registerToken(@Body body: RegisterPushTokenRequest): Response<MessageResponse>
}

data class RegisterPushTokenRequest(val token: String, val platform: String = "android")
