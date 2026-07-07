package com.vasilisneo.trackstar.ui.screens.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthRepository
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors the state LoginViewModel exposes on iOS (isLoading, isGoogleLoading,
 * errorMessage, isLoginEnabled) — minus Apple sign-in, which doesn't exist on Android.
 * login() calls the real POST /api/auth/login and persists the JWT via TokenStore;
 * loginWithGoogle() is still a stub (no Google Sign-In SDK wired yet).
 */
class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStore = TokenStore(app)
    private val repository = AuthRepository(tokenStore)

    /** Email of the last signed-in user (if cached), used by Landing's "Continue as".
     *  Must be actual Compose state (not a plain getter over SharedPreferences) so that
     *  forgetCachedUser() clearing the cache is reflected without a recomposition trigger. */
    var cachedEmail by mutableStateOf(if (tokenStore.hasCachedCredentials) tokenStore.lastEmail else null)
        private set

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

    /** "Not you?" — forget the cached user so the Continue-as card disappears. */
    fun forgetCachedUser() {
        tokenStore.clearAll()
        cachedEmail = null
    }

    fun loginWithGoogle() {
        if (isLoading || isGoogleLoading) return
        viewModelScope.launch {
            isGoogleLoading = true
            delay(800)
            isGoogleLoading = false
        }
    }
}
