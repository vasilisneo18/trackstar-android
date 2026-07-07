package com.vasilisneo.trackstar.ui.screens.register

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.RegisterRequest
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthRepository
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

enum class EmailCheckStatus { IDLE, CHECKING, AVAILABLE, EXISTS }

/** Mirrors iOS's UserGender (CreateUserProfileRequest.swift) — just male/female, no "prefer not to say". */
enum class UserGender { FEMALE, MALE }

/** Mirrors iOS's FitnessLevel (Model/AthleteNotes.swift). */
enum class FitnessLevel(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    ELITE("Elite"),
}

/** Mirrors iOS's UserGoal (ViewModel/RegisterViewModel.swift). Icon/color mapping lives in
 *  GoalsScreen.kt (UI concern), same split FitnessLevel uses with FitnessProfileScreen.kt. */
enum class UserGoal(val label: String, val subtitle: String, val isCoachGoal: Boolean = false) {
    LOSE_FAT("Lose Fat", "Burn fat & drop weight"),
    BUILD_MUSCLE("Build Muscle", "Gain strength & size"),
    TRACK_TRAINING("Track & Log", "Record every session"),
    PERFORMANCE("Performance", "Hit new records"),
    EAT_BETTER("Eat Better", "Plan meals & macros"),
    COACHING("Get Coaching", "Work with a coach"),
    MONITOR_ATHLETES("Monitor Athletes", "Manage clients & track progress", isCoachGoal = true),
}

/**
 * Mirrors iOS's RegisterViewModel — shared across all 5 registration steps (Email Entry,
 * Create Password, Personal Details, Body Metrics, Fitness Profile, Goals) via Navigation
 * Compose's nested-graph pattern, same shape as iOS's single shared instance passed down
 * the whole NavigationStack.
 *
 * checkEmail() and register() now hit the real backend (POST /api/auth/check-email and
 * /api/auth/register); register() persists the returned JWT via TokenStore.
 */
class RegisterViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AuthRepository(TokenStore(app))

    var isRegistering by mutableStateOf(false)
        private set

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

    // Fitness Profile (step 4/5)
    var fitnessLevel by mutableStateOf(FitnessLevel.BEGINNER)
        private set
    var trainingDaysPerWeek by mutableStateOf(3)
        private set

    // Goals (step 5/5)
    var goals by mutableStateOf<Set<UserGoal>>(emptySet())
        private set

    val isGoalsValid: Boolean
        get() = goals.isNotEmpty()

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

    fun onFitnessLevelChange(value: FitnessLevel) { fitnessLevel = value }
    fun onTrainingDaysPerWeekChange(value: Int) { trainingDaysPerWeek = value }

    fun toggleGoal(goal: UserGoal) {
        goals = if (goals.contains(goal)) goals - goal else goals + goal
        errorMessage = null
    }

    /** Real POST /api/auth/check-email — routes to Create Password if new, or the
     *  "account found" sheet if it already exists. */
    fun checkEmail() {
        if (email.isEmpty()) return
        emailCheckStatus = EmailCheckStatus.CHECKING
        errorMessage = null
        viewModelScope.launch {
            when (val result = repository.checkEmail(email)) {
                is ApiResult.Success ->
                    emailCheckStatus = if (result.data) EmailCheckStatus.EXISTS else EmailCheckStatus.AVAILABLE
                is ApiResult.Error -> {
                    emailCheckStatus = EmailCheckStatus.IDLE
                    errorMessage = result.message
                }
            }
        }
    }

    /** Real POST /api/auth/register from everything collected across the flow. Role is
     *  inferred from goals (monitorAthletes -> coach), matching iOS. targetWeight, goals,
     *  fitnessLevel, and trainingDaysPerWeek aren't part of the register payload (iOS keeps
     *  those client-side too). */
    fun register(onSuccess: () -> Unit) {
        if (isRegistering) return
        isRegistering = true
        errorMessage = null
        val request = RegisterRequest(
            email = email.trim().lowercase(),
            password = password,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            age = Period.between(dateOfBirth, LocalDate.now()).years.takeIf { it > 0 },
            role = if (goals.contains(UserGoal.MONITOR_ATHLETES)) "coach" else "athlete",
            gender = (gender ?: UserGender.MALE).name.lowercase(),
            height = heightCm.toDoubleOrNull(),
            weight = weightKg.toDoubleOrNull(),
            country = country.trim().ifBlank { null },
        )
        viewModelScope.launch {
            when (val result = repository.register(request)) {
                is ApiResult.Success -> {
                    isRegistering = false
                    onSuccess()
                }
                is ApiResult.Error -> {
                    isRegistering = false
                    errorMessage = result.message
                }
            }
        }
    }
}
