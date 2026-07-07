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
    private val repository = AuthRepository(TokenStore(app))

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
                    onSuccess()
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
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
