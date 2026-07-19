package com.vasilisneo.trackstar.data.api

import com.google.gson.annotations.SerializedName

// Request/response shapes matching fitness-book-api's auth DTOs
// (com.fitnessbook.dto.*). See the backend AuthController for the endpoints.

data class CheckEmailRequest(val email: String)
data class CheckEmailResponse(val exists: Boolean)

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val age: Int?,
    val role: String,           // "athlete" | "coach"
    val gender: String,         // "male" | "female"
    val height: Double?,
    val weight: Double?,
    val country: String?,
)

data class AuthResponse(
    val token: String,
    val refreshToken: String?,
    val userId: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    // Jackson serialises the Java `boolean isNewUser` getter as JSON field "newUser".
    @SerializedName("newUser") val isNewUser: Boolean = false,
)

// Social sign-in (Google/Apple). provider = "google" | "apple"; identityToken is the ID token from
// the provider that the backend verifies. firstName/lastName/email are only used when creating a
// brand-new account (the backend derives the email from the verified token for Google).
data class SocialAuthRequest(
    val provider: String,
    val identityToken: String,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
)

data class RefreshRequest(val refreshToken: String)

data class ForgotPasswordRequest(val email: String)

/** Error bodies come back as {"message": "..."}. */
data class MessageResponse(val message: String?)
