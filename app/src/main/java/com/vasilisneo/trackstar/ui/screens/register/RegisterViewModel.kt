package com.vasilisneo.trackstar.ui.screens.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class EmailCheckStatus { IDLE, CHECKING, AVAILABLE, EXISTS }

/** Mirrors iOS's UserGender (CreateUserProfileRequest.swift) — just male/female, no "prefer not to say". */
enum class UserGender { FEMALE, MALE }

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

    // Personal Details (step 2/5)
    var firstName by mutableStateOf("")
        private set
    var lastName by mutableStateOf("")
        private set
    var gender by mutableStateOf<UserGender?>(null)
        private set
    var dateOfBirth by mutableStateOf(LocalDate.now().minusYears(20))
        private set
    var country by mutableStateOf("")
        private set

    val isPersonalDetailsValid: Boolean
        get() = firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()

    // Body Metrics (step 3/5) — always stored in metric (cm/kg); ft-in and st/lb are
    // local UI-only conversions on the screen itself, same as iOS's local @State.
    var heightCm by mutableStateOf("")
        private set
    var weightKg by mutableStateOf("")
        private set
    var targetWeightKg by mutableStateOf("")
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

    fun onFirstNameChange(value: String) { firstName = value; errorMessage = null }
    fun onLastNameChange(value: String) { lastName = value; errorMessage = null }
    fun onGenderChange(value: UserGender) { gender = value }
    fun onDateOfBirthChange(value: LocalDate) { dateOfBirth = value }
    fun onCountryChange(value: String) { country = value }

    fun onHeightCmChange(value: String) { heightCm = value }
    fun onWeightKgChange(value: String) { weightKg = value }
    fun onTargetWeightKgChange(value: String) { targetWeightKg = value }

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
