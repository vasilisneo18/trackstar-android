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
import kotlinx.coroutines.launch

/**
 * Mirrors iOS's ForgotPasswordView state (email/isLoading/errorMessage/didSubmit). submit()
 * calls the real POST /api/auth/forgot-password. The backend always returns success ("if
 * that email exists…"), so didSubmit flips to the confirmation state on any non-error
 * response.
 */
class ForgotPasswordViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AuthRepository(TokenStore(app))

    var email by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var didSubmit by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        errorMessage = null
    }

    fun submit() {
        if (email.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = repository.forgotPassword(email)) {
                is ApiResult.Success -> {
                    isLoading = false
                    didSubmit = true
                }
                is ApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }
}
