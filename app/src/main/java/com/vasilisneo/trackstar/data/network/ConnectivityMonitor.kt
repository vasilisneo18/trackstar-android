package com.vasilisneo.trackstar.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Tracks whether the device currently has a usable internet connection, as a StateFlow the UI can
// collect (drives OfflineBanner) and repositories/sync can read. Initialized once from
// Application.onCreate. The Android analogue of iOS's NWPathMonitor-backed offline detection.
object ConnectivityMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun start(context: Context) {
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return

        // Seed from the current active network so the first frame is accurate.
        _isOnline.value = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }
}
