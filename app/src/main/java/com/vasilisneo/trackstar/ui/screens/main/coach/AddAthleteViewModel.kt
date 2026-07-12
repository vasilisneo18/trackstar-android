package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.launch

// Backs the add-athlete flow (iOS's AddAthleteSheet): generates a coach invite deep link (for the
// QR / share options) and adds an athlete by email. Both hit /api/coach/*.
class AddAthleteViewModel : ViewModel() {

    private val repo = AthleteRepository()

    var inviteDeepLink by mutableStateOf<String?>(null)
        private set
    var isCreatingInvite by mutableStateOf(false)
        private set

    var isAdding by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    init { createInvite() }

    fun createInvite() {
        if (inviteDeepLink != null || isCreatingInvite) return
        viewModelScope.launch {
            isCreatingInvite = true
            when (val r = repo.createInvite()) {
                is ApiResult.Success -> inviteDeepLink = r.data.deepLink
                is ApiResult.Error -> Unit
            }
            isCreatingInvite = false
        }
    }

    fun addAthlete(email: String, onSuccess: () -> Unit) {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || isAdding) return
        viewModelScope.launch {
            isAdding = true
            addError = null
            when (val r = repo.addAthlete(trimmed)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> addError = r.message
            }
            isAdding = false
        }
    }
}
