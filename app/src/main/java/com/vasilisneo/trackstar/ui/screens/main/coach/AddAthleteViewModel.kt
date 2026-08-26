package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.ProfileRepository
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.launch

// Backs the add-athlete flow (iOS's AddAthleteSheet): generates a coach invite deep link (for the
// QR / share options) and adds an athlete by email. Both hit /api/coach/*.
class AddAthleteViewModel : ViewModel() {

    private val repo = AthleteRepository()
    private val profileRepo = ProfileRepository()

    var inviteDeepLink by mutableStateOf<String?>(null)
        private set
    var isCreatingInvite by mutableStateOf(false)
        private set

    // Whether accepting the current invite (QR "My QR" / Share Link) spends a Bronze credit.
    private var inviteToken: String? = null
    var inviteGrantBronze by mutableStateOf(false)
        private set

    var isAdding by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    // Coach's free Bronze grants left, and whether to spend one on this athlete (iOS parity).
    var bronzeGrantsRemaining by mutableStateOf(0)
        private set
    var useBronzeGrant by mutableStateOf(false)

    init {
        createInvite()
        viewModelScope.launch {
            (profileRepo.getProfile() as? ApiResult.Success)?.let { bronzeGrantsRemaining = it.data.bronzeGrantsRemaining ?: 0 }
        }
    }

    fun createInvite() {
        if (inviteDeepLink != null || isCreatingInvite) return
        viewModelScope.launch {
            isCreatingInvite = true
            when (val r = repo.createInvite()) {
                is ApiResult.Success -> { inviteDeepLink = r.data.deepLink; inviteToken = r.data.token }
                is ApiResult.Error -> Unit
            }
            isCreatingInvite = false
        }
    }

    // One Bronze decision, made up front when a connection option is chosen. Arms the direct-add
    // flag (email / QR scan) and stamps the current invite (QR "My QR" / Share Link) together.
    fun setGrantChoice(grant: Boolean) {
        useBronzeGrant = grant
        applyInviteGrantBronze(grant)
    }

    // Flip whether the current invite grants Bronze on accept. Optimistic; reverts on failure.
    private fun applyInviteGrantBronze(grant: Boolean) {
        val token = inviteToken ?: return
        inviteGrantBronze = grant
        viewModelScope.launch {
            if (repo.updateInviteGrantBronze(token, grant) is ApiResult.Error) inviteGrantBronze = !grant
        }
    }

    fun clearError() { addError = null }

    fun addAthlete(email: String, onSuccess: () -> Unit) {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || isAdding) return
        viewModelScope.launch {
            isAdding = true
            addError = null
            when (val r = repo.addAthlete(trimmed, useBronzeGrant = useBronzeGrant && bronzeGrantsRemaining > 0)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.Error -> addError = r.message
            }
            isAdding = false
        }
    }
}
