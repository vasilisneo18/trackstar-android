package com.vasilisneo.trackstar.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors the state LoginViewModel exposes on iOS (isLoading, isGoogleLoading,
 * errorMessage, isLoginEnabled) — minus Apple sign-in, which doesn't exist on Android.
 * No backend call is wired up yet — networking (Retrofit/OkHttp against
 * fitness-book-api's /api/auth/login, JWT storage) is a separate piece of work.
 * login()/loginWithGoogle() only drive the loading state so the UI states (spinner,
 * disabled opacity) are real and testable.
 */
class LoginViewModel : ViewModel() {
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

    fun login() {
        if (!isLoginEnabled || isLoading) return
        viewModelScope.launch {
            isLoading = true
            delay(800)
            isLoading = false
        }
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
