package com.vasilisneo.trackstar.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors iOS's ForgotPasswordView's local @State (it has no dedicated ViewModel on iOS
 * either — the view holds email/isLoading/errorMessage/didSubmit itself). submit() is a
 * local simulation, not a real POST to a reset-password endpoint — networking isn't wired
 * up yet anywhere in this app.
 */
class ForgotPasswordViewModel : ViewModel() {
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
        if (email.isBlank()) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            delay(800)
            isLoading = false
            didSubmit = true
        }
    }
}
