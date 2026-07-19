package com.vasilisneo.trackstar.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vasilisneo.trackstar.BuildConfig

// Wraps AndroidX Credential Manager to run "Sign in with Google" and hand back a verified Google ID
// token (which the backend re-verifies at /api/auth/social). Requires an Android OAuth client
// registered in Google Cloud (same project as GOOGLE_SERVER_CLIENT_ID) with the app's package name
// and signing SHA-1 — until that exists, getCredential fails and we surface a friendly message.
object GoogleSignInManager {

    // The web client ID the backend verifies tokens against. Blank until configured (see build.gradle).
    private val serverClientId: String get() = BuildConfig.GOOGLE_SERVER_CLIENT_ID

    val isConfigured: Boolean get() = serverClientId.isNotBlank()

    // User backed out of the account chooser — callers should stay silent rather than show an error.
    class Cancelled : Exception()

    suspend fun signIn(context: Context): Result<GoogleIdTokenCredential> {
        if (!isConfigured) {
            return Result.failure(IllegalStateException("Google sign-in isn't available yet."))
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // false = offer every Google account on the device, not only ones that have signed in
            // before, so a first-time user isn't shown an empty chooser.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data))
            } else {
                Result.failure(IllegalStateException("Unexpected credential type from Google."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Cancelled())
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account found on this device."))
        } catch (e: GetCredentialException) {
            Result.failure(IllegalStateException("Google sign-in failed. Please try again."))
        }
    }
}
