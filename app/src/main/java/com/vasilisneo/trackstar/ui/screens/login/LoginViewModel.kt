package com.vasilisneo.trackstar.ui.screens.login

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthRepository
import com.vasilisneo.trackstar.data.auth.GoogleSignInManager
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.launch

/**
 * Mirrors the state LoginViewModel exposes on iOS (isLoading, isGoogleLoading,
 * errorMessage, isLoginEnabled) — minus Apple sign-in, which doesn't exist on Android.
 * login() calls the real POST /api/auth/login and persists the JWT via TokenStore;
 * loginWithGoogle() runs Credential Manager + POST /api/auth/social (needs the Android OAuth
 * client configured in Google Cloud to actually return a token — see GoogleSignInManager).
 */
class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStore = TokenStore(app)
    private val repository = AuthRepository(tokenStore)

    /** Email of the last signed-in user (if cached), used by Landing's "Continue as".
     *  Actual Compose state (not a plain getter over SharedPreferences) so dismissing the
     *  card is reflected without a recomposition trigger. Landing calls refreshCachedEmail()
     *  on every fresh appearance — mirrors iOS's InitialView.onAppear re-checking
     *  canQuickLogin from Keychain each time the screen is shown, not just once. */
    var cachedEmail by mutableStateOf(if (tokenStore.hasCachedCredentials) tokenStore.lastEmail else null)
        private set

    fun refreshCachedEmail() {
        cachedEmail = if (tokenStore.hasCachedCredentials) tokenStore.lastEmail else null
    }

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var showPassword by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isGoogleLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isLoginEnabled: Boolean
        get() = email.isNotBlank() && password.isNotBlank()

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun toggleShowPassword() {
        showPassword = !showPassword
    }

    fun login(onSuccess: () -> Unit = {}) {
        if (!isLoginEnabled || isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = repository.login(email, password)) {
                is ApiResult.Success -> {
                    isLoading = false
                    cachedEmail = tokenStore.lastEmail
                    onSuccess()
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    /** One-tap re-login using the cached credentials (Landing's "Continue as" card). */
    fun quickLogin(onSuccess: () -> Unit = {}) {
        val loginPassword = tokenStore.lastPassword ?: return
        val loginEmail = tokenStore.lastEmail ?: return
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = repository.login(loginEmail, loginPassword)) {
                is ApiResult.Success -> {
                    isLoading = false
                    onSuccess()
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    /** "Not you?" — mirrors iOS: only dismisses the card for this appearance of Landing.
     *  Does NOT clear the cached credentials (iOS's "Not you?" never touches Keychain
     *  either) — the card reappears next time Landing is (re)shown, e.g. after pushing
     *  Login and navigating back. Cached credentials are only actually wiped by logging
     *  in as someone else (overwrites them) or Close Account (tokenStore.clearAll()). */
    fun dismissContinueAs() {
        cachedEmail = null
    }

    /** Sign in with Google: launch the account chooser, then exchange the Google ID token for our
     *  JWT via POST /api/auth/social. `context` must be the hosting Activity (Credential Manager
     *  shows its UI from it). Cancelling the chooser is silent; other failures surface a message. */
    fun loginWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        if (isLoading || isGoogleLoading) return
        viewModelScope.launch {
            isGoogleLoading = true
            errorMessage = null

            val credential = GoogleSignInManager.signIn(context).getOrElse { e ->
                isGoogleLoading = false
                when (e) {
                    // Backed out of the chooser — stay silent.
                    is GoogleSignInManager.Cancelled -> {}
                    // No Google account on the device — open the system add-account screen so they
                    // can add one, then tap Continue with Google again.
                    is GoogleSignInManager.NoAccount -> {
                        GoogleSignInManager.launchAddGoogleAccount(context)
                        errorMessage = "Add a Google account, then tap Continue with Google again."
                    }
                    else -> errorMessage = e.message ?: "Google sign-in failed."
                }
                return@launch
            }

            when (val result = repository.socialLogin(
                provider = "google",
                idToken = credential.idToken,
                firstName = credential.givenName,
                lastName = credential.familyName,
                email = credential.id,
            )) {
                is ApiResult.Success -> {
                    isGoogleLoading = false
                    cachedEmail = tokenStore.lastEmail
                    onSuccess()
                }
                is ApiResult.Error -> {
                    isGoogleLoading = false
                    errorMessage = result.message
                }
            }
        }
    }
}
