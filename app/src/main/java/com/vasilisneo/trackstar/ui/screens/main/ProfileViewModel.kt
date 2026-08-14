package com.vasilisneo.trackstar.ui.screens.main

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.UpdateProfileRequest
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.ProfileRepository
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.launch

// Loads the signed-in user's profile from GET /api/profile, falling back to the name/email
// cached in TokenStore while the request is in flight (or if it fails offline).
// repository is constructor-injected so tests can supply a fake. The secondary (Application)
// constructor is the one Compose's viewModel() factory resolves at runtime.
class ProfileViewModel(
    app: Application,
    private val repository: ProfileRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, ProfileRepository())

    private val tokenStore = TokenStore(app)

    var profile by mutableStateOf<ProfileResponse?>(null)
        private set

    // Immediate best-effort identity from the cached session, shown before the fetch lands.
    val cachedFullName: String =
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { "Trackstar User" }
    val cachedEmail: String = tokenStore.email ?: "—"

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            // Paint the cached profile first so body stats render instantly, then refresh.
            if (profile == null) repository.cachedProfile()?.let { profile = it }
            when (val result = repository.getProfile()) {
                is ApiResult.Success -> profile = result.data
                is ApiResult.Error -> Unit // keep cached values on failure
            }
        }
    }

    // Persist a partial profile edit (Personal Info screen) and reflect the server's response locally.
    fun save(request: UpdateProfileRequest) {
        viewModelScope.launch {
            when (val r = repository.updateProfile(request)) {
                is ApiResult.Success -> profile = r.data
                is ApiResult.Error -> Unit
            }
        }
    }

    fun logout() = tokenStore.clear()
}
