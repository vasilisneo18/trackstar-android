package com.vasilisneo.trackstar.ui.screens.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class EmailCheckStatus { IDLE, CHECKING, AVAILABLE, EXISTS }

/**
 * Mirrors iOS's RegisterViewModel, scoped for now to what the Email Entry and Create
 * Password steps need (email, password, confirmPassword, emailCheckStatus, errorMessage).
 * Fields for the remaining steps (personal details, body metrics, fitness profile, goals)
 * get added here once those screens are built — same shared-across-the-whole-flow shape
 * as iOS, just built incrementally.
 *
 * checkEmail() is a local simulation, not a real POST /api/auth/check-email call —
 * networking isn't wired up yet anywhere in this app.
 */
class RegisterViewModel : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var showPassword by mutableStateOf(false)
        private set
    var showConfirmPassword by mutableStateOf(false)
        private set
    var emailCheckStatus by mutableStateOf(EmailCheckStatus.IDLE)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isValidEmail: Boolean
        get() = email.contains("@") && email.contains(".")

    val isPasswordValid: Boolean
        get() = password.length >= 8

    val passwordsMatch: Boolean
        get() = confirmPassword.isNotEmpty() && password == confirmPassword

    fun onEmailChange(value: String) {
        email = value
        emailCheckStatus = EmailCheckStatus.IDLE
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
        errorMessage = null
    }

    fun toggleShowPassword() {
        showPassword = !showPassword
    }

    fun toggleShowConfirmPassword() {
        showConfirmPassword = !showConfirmPassword
    }

    fun resetEmailCheckStatus() {
        emailCheckStatus = EmailCheckStatus.IDLE
    }

    /** Simulates the exists/available check — every email is treated as new/available for now. */
    fun checkEmail() {
        if (email.isEmpty()) return
        emailCheckStatus = EmailCheckStatus.CHECKING
        errorMessage = null
        viewModelScope.launch {
            delay(800)
            emailCheckStatus = EmailCheckStatus.AVAILABLE
        }
    }
}
