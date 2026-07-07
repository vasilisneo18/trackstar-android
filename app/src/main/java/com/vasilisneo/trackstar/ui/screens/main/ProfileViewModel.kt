package com.vasilisneo.trackstar.ui.screens.main

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.ProfileRepository
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.launch

// Loads the signed-in user's profile from GET /api/profile, falling back to the name/email
// cached in TokenStore while the request is in flight (or if it fails offline).
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ProfileRepository()
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
            when (val result = repository.getProfile()) {
                is ApiResult.Success -> profile = result.data
                is ApiResult.Error -> Unit // keep cached values on failure
            }
        }
    }

    fun logout() = tokenStore.clear()
}
